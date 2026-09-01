package com.novastream.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.R
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPlaybackScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_playback), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPure)
            )
        },
        containerColor = BgPure
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_autoplay_title),
                subtitle = stringResource(R.string.settings_autoplay_subtitle),
                checked = state.autoplayNext,
                onChecked = vm::setAutoplayNext
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_skip_intro_title),
                subtitle = stringResource(R.string.settings_skip_intro_subtitle),
                checked = state.skipIntroButton,
                onChecked = vm::setSkipIntroButton
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_data_saver_title),
                subtitle = stringResource(R.string.settings_data_saver_subtitle),
                checked = state.dataSaverMode,
                onChecked = vm::setDataSaverMode
            )
            Text(
                stringResource(R.string.settings_preferred_hoster_title),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            PREFERRED_HOSTERS.forEach { hoster ->
                SettingsChipRow(
                    label = hoster,
                    selected = state.preferredHoster == hoster,
                    onClick = { vm.setPreferredHoster(hoster) }
                )
            }
            Text(
                stringResource(R.string.settings_playback_speed_title),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                SettingsChipRow(
                    label = "${speed}x",
                    selected = state.playbackSpeed == speed,
                    onClick = { vm.setPlaybackSpeed(speed) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_design), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPure)
            )
        },
        containerColor = BgPure
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = state.dynamicColor,
                onChecked = vm::setDynamicColor
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_reduce_motion_title),
                subtitle = stringResource(R.string.settings_reduce_motion_subtitle),
                checked = state.reduceMotion,
                onChecked = vm::setReduceMotion
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_performance_mode_title),
                subtitle = stringResource(R.string.settings_performance_mode_subtitle),
                checked = state.performanceMode,
                onChecked = vm::setPerformanceMode
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdvancedScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = androidx.compose.runtime.remember { AppSettings(context) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_advanced), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPure)
            )
        },
        containerColor = BgPure
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsUltraSections(appSettings = appSettings, onOpenDownloads = onOpenDownloads)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun SettingsChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

private val PREFERRED_HOSTERS = listOf("VOE", "Streamtape", "Doodstream", "Filemoon", "Vidoza", "Vidmoly", "Mixdrop")
