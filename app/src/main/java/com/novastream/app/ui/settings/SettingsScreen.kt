package com.novastream.app.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val message: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val watchRepo = WatchRepository(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            watchRepo.watchlist().collect { _state.update { it.copy(watchlistCount = it.watchlistCount) } }
        }
        viewModelScope.launch {
            watchRepo.watchProgress().collect { _state.update { it.copy(continueWatchingCount = it.continueWatchingCount) } }
        }
    }

    fun clearContinueWatching() {
        viewModelScope.launch {
            watchRepo.clearAllProgress()
            _state.update { it.copy(message = "Continue Watching wurde geleert") }
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
}

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            // Section: Daten
            SettingsSectionHeader("Datenverwaltung")
            SettingsItem(
                icon = Icons.Default.PlayCircle,
                title = "Continue Watching leeren",
                subtitle = "Entfernt alle gespeicherten Wiedergabefortschritte",
                onClick = { vm.clearContinueWatching() }
            )
            SettingsItem(
                icon = Icons.Default.CleaningServices,
                title = "Abgeschlossene Episoden entfernen",
                subtitle = "Löscht Episoden die zu >90% geschaut wurden",
                onClick = { vm.clearCompleted() }
            )
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Watchlist leeren",
                subtitle = "Entfernt alle Serien aus deiner Watchlist",
                onClick = { vm.clearWatchlist() }
            )

            Spacer(Modifier.height(24.dp))

            // Section: Über
            SettingsSectionHeader("Über NovaStream")
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0"
            )
            SettingsInfoItem(
                icon = Icons.Default.BugReport,
                title = "Quellcode",
                subtitle = "github.com/dskja/NovaStream"
            )

            Spacer(Modifier.height(80.dp))
        }
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
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
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
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
    }
}
