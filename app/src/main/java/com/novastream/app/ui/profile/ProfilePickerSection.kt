package com.novastream.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.R
import com.novastream.app.data.db.ProfileEntity
import kotlinx.coroutines.launch

@Composable
fun ProfilePickerSection(onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ProfilePickerViewModel = hiltViewModel()
    val profileManager = vm.profileManager
    val profiles by profileManager.observeProfiles().collectAsStateWithLifecycle(initialValue = emptyList())
    var newName by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newIsKids by remember { mutableStateOf(false) }
    var pinDialogProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinEditProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinEditValue by remember { mutableStateOf("") }
    var deleteProfileTarget by remember { mutableStateOf<ProfileEntity?>(null) }

    LaunchedEffect(Unit) {
        profileManager.ensureDefaultProfile()
    }

    profiles.forEach { profile ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (profile.isActive) return@clickable
                    if (profile.requiresPin) {
                        pinDialogProfile = profile
                        pinInput = ""
                    } else {
                        scope.launch {
                            val ok = profileManager.switchProfile(profile.profileId, null)
                            onStatus(
                                if (ok) context.getString(R.string.settings_profile_switched, profile.displayName)
                                else context.getString(R.string.settings_profile_switch_failed)
                            )
                        }
                    }
                }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(profile.avatarEmoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                if (profile.isKids) {
                    Text(stringResource(R.string.settings_profile_kids), style = MaterialTheme.typography.bodySmall)
                }
                if (profile.requiresPin) {
                    Text(stringResource(R.string.settings_profile_pin_protected), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (profile.isActive) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = {
                pinEditProfile = profile
                pinEditValue = ""
            }) {
                Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.settings_profile_change_pin))
            }
            if (profile.profileId != ProfileEntity.DEFAULT_ID) {
                IconButton(onClick = { deleteProfileTarget = profile }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_profile_delete))
                }
            }
        }
    }

    OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_profile_name)) },
        singleLine = true
    )
    OutlinedTextField(
        value = newPin,
        onValueChange = { newPin = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_profile_pin_optional)) },
        singleLine = true
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = newIsKids, onCheckedChange = { newIsKids = it })
        Text(stringResource(R.string.settings_profile_kids))
    }
    TextButton(
        onClick = {
            if (newName.isBlank()) return@TextButton
            scope.launch {
                profileManager.createProfile(
                    name = newName.trim(),
                    pin = newPin.takeIf { it.isNotBlank() },
                    isKids = newIsKids
                )
                newName = ""
                newPin = ""
                newIsKids = false
                onStatus(context.getString(R.string.settings_profile_created))
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.settings_profile_create))
    }

    pinDialogProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pinDialogProfile = null },
            title = { Text(stringResource(R.string.settings_profile_enter_pin, profile.displayName)) },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text(stringResource(R.string.settings_profile_pin)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = profileManager.switchProfile(profile.profileId, pinInput)
                        pinDialogProfile = null
                        onStatus(
                            if (ok) context.getString(R.string.settings_profile_switched, profile.displayName)
                            else context.getString(R.string.settings_profile_pin_wrong)
                        )
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pinDialogProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pinEditProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pinEditProfile = null },
            title = { Text(stringResource(R.string.settings_profile_change_pin_title, profile.displayName)) },
            text = {
                OutlinedTextField(
                    value = pinEditValue,
                    onValueChange = { pinEditValue = it },
                    label = { Text(stringResource(R.string.settings_profile_pin_new_or_empty)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        profileManager.setPin(profile.profileId, pinEditValue.takeIf { it.isNotBlank() })
                        pinEditProfile = null
                        onStatus(context.getString(R.string.settings_profile_pin_updated))
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pinEditProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    deleteProfileTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfileTarget = null },
            title = { Text(stringResource(R.string.settings_profile_delete_title)) },
            text = { Text(stringResource(R.string.settings_profile_delete_message, profile.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        profileManager.deleteProfile(profile.profileId)
                        deleteProfileTarget = null
                        onStatus(context.getString(R.string.settings_profile_deleted, profile.displayName))
                    }
                }) { Text(stringResource(R.string.settings_profile_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfileTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
