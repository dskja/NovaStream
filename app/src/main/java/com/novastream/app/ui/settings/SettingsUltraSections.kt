package com.novastream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.R
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.download.DownloadForegroundService
import com.novastream.app.provider.SiteProfileImporter
import com.novastream.app.sync.BackupRestoreManager
import com.novastream.app.sync.CloudSyncManager
import com.novastream.app.ui.profile.ProfilePickerSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** v12–v15 advanced settings sections (IPTV, sync, downloads, telemetry, profiles). */
@Composable
fun SettingsUltraSections(
    appSettings: AppSettings,
    onOpenDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playTelemetry by appSettings.playTelemetry.collectAsStateWithLifecycle(initialValue = false)
    val iptvEnabled by appSettings.iptvEnabled.collectAsStateWithLifecycle(initialValue = false)
    val castEnabled by appSettings.castEnabled.collectAsStateWithLifecycle(initialValue = true)
    var m3uUrl by remember { mutableStateOf("") }
    var syncUrl by remember { mutableStateOf("") }
    var syncKey by remember { mutableStateOf("") }
    var profileJson by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(appSettings) {
        m3uUrl = appSettings.userM3uUrl.first()
        syncUrl = appSettings.syncUrl.first()
        syncKey = appSettings.syncDeviceKey.first()
        epgUrl = appSettings.epgUrl.first()
    }

    SettingsSectionHeader(stringResource(R.string.settings_ultra_playback))
    SettingsToggle(stringResource(R.string.settings_play_telemetry_title),
        stringResource(R.string.settings_play_telemetry_subtitle), playTelemetry) {
        scope.launch { appSettings.setPlayTelemetry(it) }
    }
    SettingsToggle(stringResource(R.string.settings_cast_title),
        stringResource(R.string.settings_cast_subtitle), castEnabled) {
        scope.launch { appSettings.setCastEnabled(it) }
    }

    SettingsSectionHeader(stringResource(R.string.settings_offline_downloads))
    SettingsAction(stringResource(R.string.settings_open_downloads),
        stringResource(R.string.settings_open_downloads_sub)) {
        onOpenDownloads()
    }
    SettingsAction(stringResource(R.string.settings_downloads_init),
        stringResource(R.string.settings_downloads_init_sub)) {
        DownloadForegroundService.ensureChannel(context)
        statusMessage = context.getString(R.string.settings_downloads_ready)
    }

    SettingsSectionHeader(stringResource(R.string.settings_iptv))
    SettingsToggle(stringResource(R.string.settings_iptv_enable),
        stringResource(R.string.settings_iptv_enable_sub), iptvEnabled) {
        scope.launch { appSettings.setIptvEnabled(it) }
    }
    OutlinedTextField(
        value = m3uUrl,
        onValueChange = { m3uUrl = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_m3u_url)) },
        singleLine = true
    )
    SettingsAction(stringResource(R.string.settings_m3u_save), "") {
        scope.launch { appSettings.setUserM3uUrl(m3uUrl) }
        statusMessage = context.getString(R.string.settings_m3u_saved)
    }
    OutlinedTextField(
        value = epgUrl,
        onValueChange = { epgUrl = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_epg_url)) },
        singleLine = true
    )
    SettingsAction(stringResource(R.string.settings_epg_save), "") {
        scope.launch { appSettings.setEpgUrl(epgUrl) }
        statusMessage = context.getString(R.string.settings_epg_saved)
    }

    SettingsSectionHeader(stringResource(R.string.settings_sync_backup))
    SettingsAction(stringResource(R.string.settings_export_backup), "") {
        scope.launch {
            val file = BackupRestoreManager(context, NovaStreamDatabase.get(context)).exportToFile()
            statusMessage = context.getString(R.string.settings_export_done, file.name)
        }
    }
    OutlinedTextField(
        value = syncUrl,
        onValueChange = { syncUrl = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_sync_url)) },
        singleLine = true
    )
    OutlinedTextField(
        value = syncKey,
        onValueChange = { syncKey = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_sync_key)) },
        singleLine = true
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            scope.launch {
                appSettings.setSyncUrl(syncUrl)
                appSettings.setSyncDeviceKey(syncKey)
                val mgr = CloudSyncManager(context, BackupRestoreManager(context, NovaStreamDatabase.get(context)), appSettings)
                statusMessage = when (val r = mgr.pushToRemote()) {
                    is CloudSyncManager.SyncResult.Success -> r.message
                    is CloudSyncManager.SyncResult.Error -> r.message
                }
            }
        }) { Text(stringResource(R.string.settings_sync_push)) }
        OutlinedButton(onClick = {
            scope.launch {
                appSettings.setSyncUrl(syncUrl)
                appSettings.setSyncDeviceKey(syncKey)
                val mgr = CloudSyncManager(context, BackupRestoreManager(context, NovaStreamDatabase.get(context)), appSettings)
                statusMessage = when (val r = mgr.pullFromRemote()) {
                    is CloudSyncManager.SyncResult.Success -> r.message
                    is CloudSyncManager.SyncResult.Error -> r.message
                }
            }
        }) { Text(stringResource(R.string.settings_sync_pull)) }
    }

    SettingsSectionHeader(stringResource(R.string.settings_profiles))
    ProfilePickerSection(onStatus = { statusMessage = it })

    SettingsSectionHeader(stringResource(R.string.settings_provider_import))
    OutlinedTextField(
        value = profileJson,
        onValueChange = { profileJson = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).heightIn(min = 80.dp),
        label = { Text(stringResource(R.string.settings_site_profile_json)) },
        minLines = 3
    )
    SettingsAction(stringResource(R.string.settings_import_site_profile), "") {
        when (val r = SiteProfileImporter.importFromJson(profileJson)) {
            is SiteProfileImporter.ImportResult.Success ->
                statusMessage = context.getString(R.string.settings_import_success, r.count)
            is SiteProfileImporter.ImportResult.Error -> statusMessage = r.message
        }
    }

    statusMessage?.let {
        Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
