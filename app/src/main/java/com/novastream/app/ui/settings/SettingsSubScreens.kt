package com.novastream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.R
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.ui.theme.*
import com.novastream.app.util.LocaleManager
import com.novastream.app.util.findActivity

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
                stringResource(R.string.settings_preferred_language_title),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            PREFERRED_LANGUAGES.forEach { language ->
                SettingsChipRow(
                    label = language,
                    selected = state.preferredLanguage == language,
                    onClick = { vm.setPreferredLanguage(language) }
                )
            }
            Text(
                stringResource(R.string.settings_playback_speed_title),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                SettingsChipRow(
                    label = "${speed}x",
                    selected = kotlin.math.abs(state.playbackSpeed - speed) < 0.01f,
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
    val context = LocalContext.current
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
            SettingsDropdownRow(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_ui_language_title),
                subtitle = stringResource(R.string.settings_ui_language_subtitle),
                selectedValue = LocaleManager.localeDisplayName(state.uiLocale),
                options = (listOf(LocaleManager.SYSTEM_LOCALE) + LocaleManager.supportedUiLocales)
                    .map { LocaleManager.localeDisplayName(it) },
                onSelect = { label ->
                    val tag = (listOf(LocaleManager.SYSTEM_LOCALE) + LocaleManager.supportedUiLocales)
                        .first { LocaleManager.localeDisplayName(it) == label }
                    if (tag != state.uiLocale) {
                        vm.setUiLocale(tag)
                        context.findActivity()?.recreate()
                    }
                }
            )
            SettingsDropdownRow(
                icon = Icons.Default.Stream,
                title = stringResource(R.string.settings_content_language_title),
                subtitle = stringResource(R.string.settings_content_language_subtitle),
                selectedValue = com.novastream.app.data.provider.ProviderLanguageManager
                    .getLanguageDisplayName(ContentLanguage.fromTag(state.contentLanguageTag)),
                options = ContentLanguage.entries.filter { it != ContentLanguage.MULTI }
                    .map { com.novastream.app.data.provider.ProviderLanguageManager.getLanguageDisplayName(it) },
                onSelect = { label ->
                    val tag = ContentLanguage.entries.filter { it != ContentLanguage.MULTI }
                        .first {
                            com.novastream.app.data.provider.ProviderLanguageManager.getLanguageDisplayName(it) == label
                        }.tag
                    vm.setContentLanguage(tag)
                }
            )
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
    val vm: SettingsViewModel = hiltViewModel()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingActionTitle by remember { mutableStateOf("") }

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

            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_data_management))
            SettingsItem(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.settings_clear_continue_title),
                subtitle = stringResource(R.string.settings_clear_continue_subtitle),
                onClick = {
                    pendingActionTitle = context.getString(R.string.settings_clear_continue_confirm)
                    pendingAction = { vm.clearContinueWatching() }
                }
            )
            SettingsItem(
                icon = Icons.Default.CleaningServices,
                title = stringResource(R.string.settings_clear_completed_title),
                subtitle = stringResource(R.string.settings_clear_completed_subtitle),
                onClick = {
                    pendingActionTitle = context.getString(R.string.settings_clear_completed_confirm)
                    pendingAction = { vm.clearCompleted() }
                }
            )
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = stringResource(R.string.settings_clear_watchlist_title),
                subtitle = stringResource(R.string.settings_clear_watchlist_subtitle),
                onClick = {
                    pendingActionTitle = context.getString(R.string.settings_clear_watchlist_confirm)
                    pendingAction = { vm.clearWatchlist() }
                }
            )
        }
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pendingActionTitle, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction?.invoke()
                    pendingAction = null
                }) {
                    Text(stringResource(R.string.confirm), color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = BgSurface
        )
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
private val PREFERRED_LANGUAGES = listOf("Deutsch", "Englisch", "Ger-Sub", "Eng-Sub")
