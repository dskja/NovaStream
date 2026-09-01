package com.novastream.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.R
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.ProfileEntity
import com.novastream.app.profile.ProfileManager
import kotlinx.coroutines.launch

@Composable
fun ProfilePickerSection(onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileManager = remember { ProfileManager(context, NovaStreamDatabase.get(context)) }
    val profiles by profileManager.observeProfiles().collectAsStateWithLifecycle(initialValue = emptyList())
    var newName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        profileManager.ensureDefaultProfile()
    }

    profiles.forEach { profile ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val ok = profileManager.switchProfile(profile.profileId, pin.takeIf { profile.requiresPin })
                        onStatus(
                            if (ok) context.getString(R.string.settings_profile_switched, profile.displayName)
                            else context.getString(R.string.settings_profile_pin_wrong)
                        )
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
            }
            if (profile.isActive) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
        value = pin,
        onValueChange = { pin = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_profile_pin_optional)) },
        singleLine = true
    )
    TextButton(
        onClick = {
            scope.launch {
                val name = newName.trim().ifBlank { "Profile ${profiles.size + 1}" }
                profileManager.createProfile(name, pin.takeIf { it.isNotBlank() })
                newName = ""
                pin = ""
                onStatus(context.getString(R.string.settings_profile_created))
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_profile_create))
    }
}
