package com.novastream.app.ui.settings

import android.app.Application
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val message: String? = null,
    val updateChecking: Boolean = false,
    val updateInfo: com.novastream.app.util.UpdateChecker.UpdateInfo? = null,
    val updateChecked: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val watchRepo = WatchRepository.get(application)
    private val appSettings = com.novastream.app.data.prefs.AppSettings(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            watchRepo.watchlist().collect { list ->
                val pid = com.novastream.app.data.provider.ActiveProvider.id
                val scoped = list.filter { it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown" }
                _state.update { it.copy(watchlistCount = scoped.size) }
            }
        }
        viewModelScope.launch {
            watchRepo.watchProgressForActiveProvider().collect { list ->
                _state.update { it.copy(continueWatchingCount = list.count { !it.isCompleted }) }
            }
        }
        // Load available providers
        _state.update {
            it.copy(
                availableProviders = com.novastream.app.data.provider.ProviderManager.getProviderInfos(),
                activeProviderId = com.novastream.app.data.provider.ActiveProvider.id
            )
        }
        // Watch active provider changes (nur UI Update - ActiveProvider wird von NovaStreamApp gesetzt)
        viewModelScope.launch {
            com.novastream.app.data.provider.ProviderManager.activeProviderIdFlow(application).collect { providerId ->
                _state.update { it.copy(activeProviderId = providerId) }
            }
        }
        // App Settings flows
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
        // Auto-check for updates once
        checkForUpdates()
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
                        info == null -> "Update-Check fehlgeschlagen oder keine Releases"
                        info.isNewer -> "Update verfügbar: v${info.latestVersion}"
                        else -> "Du bist auf dem neuesten Stand (v${info.currentVersion})"
                    }
                )
            }
        }
    }

    fun clearContinueWatching() {
        viewModelScope.launch {
            watchRepo.clearAllProgress()
            _state.update { it.copy(message = "Weitersehen wurde geleert") }
        }
    }

    fun clearWatchlist() {
        viewModelScope.launch {
            watchRepo.clearWatchlist()
            _state.update { it.copy(message = "Watchlist wurde geleert") }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            watchRepo.removeCompleted()
            _state.update { it.copy(message = "Abgeschlossene Episoden wurden entfernt") }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showUrlError() {
        _state.update { it.copy(message = "Link konnte nicht geöffnet werden") }
    }

    fun setProvider(providerId: String) {
        viewModelScope.launch {
            val provider = com.novastream.app.data.provider.ProviderManager.getProviderOrNull(providerId)
            if (provider == null) {
                _state.update { it.copy(message = "Provider nicht gefunden") }
                return@launch
            }
            com.novastream.app.data.provider.ProviderManager.setActiveProvider(getApplication(), providerId)
            com.novastream.app.data.provider.ActiveProvider.setById(providerId)
            _state.update { it.copy(message = "Provider gewechselt zu ${provider.displayName}") }
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
}

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingActionTitle by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
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
                    "Einstellungen",
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
                    label = "Weitersehen",
                    value = state.continueWatchingCount,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Watchlist",
                    value = state.watchlistCount,
                    modifier = Modifier.weight(1f)
                )
            }

            // Section: Streaming Provider
            SettingsSectionHeader("Streaming Provider")
            state.availableProviders.forEach { providerInfo ->
                val isSelected = providerInfo.id == state.activeProviderId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setProvider(providerInfo.id) }
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
                            "${providerInfo.contentLabel} · ${providerInfo.hostLabel}",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        providerInfo.catalogHint?.let { hint ->
                            Text(
                                hint,
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (isSelected) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Primary.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Aktiv",
                                color = Primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Section: Wiedergabe
            SettingsSectionHeader("Wiedergabe")

            // Autoplay toggle
            Row(
                Modifier
                    .fillMaxWidth()
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
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Autoplay nächste Folge", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Nächste Episode automatisch abspielen", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                Switch(
                    checked = state.autoplayNext,
                    onCheckedChange = { vm.setAutoplayNext(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Primary,
                        checkedTrackColor = Primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgSurfaceElevated
                    )
                )
            }

            // Skip Intro button toggle
            Row(
                Modifier
                    .fillMaxWidth()
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
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Intro überspringen", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Skip-Button im Player anzeigen", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                Switch(
                    checked = state.skipIntroButton,
                    onCheckedChange = { vm.setSkipIntroButton(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Primary,
                        checkedTrackColor = Primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgSurfaceElevated
                    )
                )
            }

            // Playback Speed selector
            Row(
                Modifier
                    .fillMaxWidth()
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
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wiedergabegeschwindigkeit", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Standard-Tempo für neue Videos", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                Text(
                    "${state.playbackSpeed}x",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            // Speed options
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val selected = kotlin.math.abs(state.playbackSpeed - speed) < 0.01f
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Primary.copy(alpha = 0.15f) else BgSurface)
                            .clickable { vm.setPlaybackSpeed(speed) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${speed}x",
                            color = if (selected) Primary else TextTertiary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Section: Design
            SettingsSectionHeader("Design")

            // Dynamic Color toggle
            Row(
                Modifier
                    .fillMaxWidth()
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
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dynamic Color", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Material You - Farben an Wallpaper anpassen (Android 12+)", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                Switch(
                    checked = state.dynamicColor,
                    onCheckedChange = { vm.setDynamicColor(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Primary,
                        checkedTrackColor = Primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgSurfaceElevated
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Section: Datenverwaltung
            SettingsSectionHeader("Datenverwaltung")

            SettingsItem(
                icon = Icons.Default.PlayCircle,
                title = "Weitersehen leeren",
                subtitle = "Entfernt alle gespeicherten Wiedergabefortschritte",
                onClick = {
                    pendingActionTitle = "Weitersehen leeren?"
                    pendingAction = { vm.clearContinueWatching() }
                }
            )
            SettingsItem(
                icon = Icons.Default.CleaningServices,
                title = "Abgeschlossene Episoden entfernen",
                subtitle = "Löscht Episoden die zu >90% geschaut wurden",
                onClick = {
                    pendingActionTitle = "Abgeschlossene Episoden entfernen?"
                    pendingAction = { vm.clearCompleted() }
                }
            )
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Watchlist leeren",
                subtitle = "Entfernt alle Serien aus deiner Watchlist",
                onClick = {
                    pendingActionTitle = "Watchlist leeren?"
                    pendingAction = { vm.clearWatchlist() }
                }
            )

            Spacer(Modifier.height(24.dp))

            // Section: Updates
            SettingsSectionHeader("App-Updates")
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
                                    state.updateChecking -> "Prüfe auf Updates…"
                                    update?.isNewer == true -> "Update verfügbar: v${update.latestVersion}"
                                    update != null -> "Aktuell: v${update.currentVersion}"
                                    else -> "Nach Updates suchen"
                                },
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "GitHub Releases · ${com.novastream.app.util.UpdateChecker.GITHUB_REPO}",
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
                                Text(if (update.downloadUrl != null) "APK laden" else "Release öffnen")
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
                                Text("Changelog")
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
            SettingsSectionHeader("Über NovaStream")

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
                        "Ein moderner Android Streaming-Client mit Continue Watching, Watchlist, Multi-Hoster Support und DNS-over-HTTPS.",
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
                title = "Quellcode",
                subtitle = "github.com/dskja/NovaStream",
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.BugReport,
                title = "Fehler melden",
                subtitle = "Issue auf GitHub erstellen",
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream/issues/new") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.Star,
                title = "Sterne vergeben",
                subtitle = "Repo auf GitHub bewerten",
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.Gavel,
                title = "Lizenz",
                subtitle = "MIT License - ansehen",
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream/blob/main/LICENSE") { vm.showUrlError() } }
            )
            ClickableSettingsItem(
                icon = Icons.Default.Security,
                title = "Datenschutz",
                subtitle = "Keine Daten werden gesammelt",
                onClick = { openUrl(context, "https://github.com/dskja/NovaStream#privacy") { vm.showUrlError() } }
            )

            Spacer(Modifier.height(24.dp))

            // Section: Entwickler
            SettingsSectionHeader("Entwickler")
            ClickableSettingsItem(
                icon = Icons.Default.Code,
                title = "dskja",
                subtitle = "GitHub Profil",
                onClick = { openUrl(context, "https://github.com/dskja") { vm.showUrlError() } }
            )

            Spacer(Modifier.height(24.dp))

            // Footer
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Made with ", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.Favorite, null, tint = Primary, modifier = Modifier.size(12.dp))
                    Text(" by dskja", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "NovaStream ist ein inoffizieller Client.\nNur für Bildungszwecke. Verwendung auf eigene Verantwortung.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Version ${com.novastream.app.BuildConfig.VERSION_NAME} · Build ${com.novastream.app.BuildConfig.VERSION_CODE}",
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

    // Confirmation Dialog for destructive actions
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pendingActionTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Diese Aktion kann nicht rückgängig gemacht werden.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction?.invoke()
                        pendingAction = null
                    }
                ) { Text("Bestätigen", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Abbrechen", color = TextTertiary)
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
private fun SettingsSectionHeader(title: String) {
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
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .focusable()
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
            .focusable()
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
