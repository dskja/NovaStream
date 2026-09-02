package com.novastream.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novastream.app.R
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable
import com.novastream.app.util.*

@Composable
fun OnboardingPermissionsStep(
    modifier: Modifier = Modifier,
    onNotificationStatusChange: (SetupPermissionStatus) -> Unit = {},
    onBatteryStatusChange: (SetupPermissionStatus) -> Unit = {}
) {
    val context = LocalContext.current
    var notificationStatus by remember {
        mutableStateOf(notificationPermissionStatus(context))
    }
    var batteryStatus by remember {
        mutableStateOf(batteryOptimizationStatus(context))
    }
    val showBattery = !isTvDevice(context)

    val requestNotifications = rememberNotificationPermissionLauncher { granted ->
        notificationStatus = if (granted) SetupPermissionStatus.GRANTED else SetupPermissionStatus.DENIED
        onNotificationStatusChange(notificationStatus)
    }

    LaunchedEffect(notificationStatus) { onNotificationStatusChange(notificationStatus) }
    LaunchedEffect(batteryStatus) { onBatteryStatusChange(batteryStatus) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationStatus = notificationPermissionStatus(context)
                batteryStatus = batteryOptimizationStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionCard(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.onboarding_perm_notifications_title),
            subtitle = stringResource(R.string.onboarding_perm_notifications_sub),
            status = notificationStatus,
            actionLabel = stringResource(R.string.onboarding_perm_allow),
            onAction = requestNotifications,
            enabled = notificationStatus != SetupPermissionStatus.GRANTED &&
                notificationStatus != SetupPermissionStatus.NOT_REQUIRED
        )
        if (showBattery) {
            PermissionCard(
                icon = Icons.Default.PowerSettingsNew,
                title = stringResource(R.string.onboarding_perm_battery_title),
                subtitle = stringResource(R.string.onboarding_perm_battery_sub),
                status = batteryStatus,
                actionLabel = stringResource(R.string.onboarding_perm_battery_action),
                onAction = {
                    openBatteryOptimizationSettings(context)
                    batteryStatus = batteryOptimizationStatus(context)
                },
                enabled = batteryStatus != SetupPermissionStatus.GRANTED &&
                    batteryStatus != SetupPermissionStatus.NOT_REQUIRED
            )
        }
        PermissionCard(
            icon = Icons.Default.Wifi,
            title = stringResource(R.string.onboarding_perm_network_title),
            subtitle = stringResource(R.string.onboarding_perm_network_sub),
            status = SetupPermissionStatus.GRANTED,
            actionLabel = null,
            onAction = {},
            enabled = false
        )
        if (notificationStatus == SetupPermissionStatus.DENIED ||
            (showBattery && batteryStatus == SetupPermissionStatus.DENIED)
        ) {
            Text(
                stringResource(R.string.onboarding_perm_optional_hint),
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: SetupPermissionStatus,
    actionLabel: String?,
    onAction: () -> Unit,
    enabled: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                permissionStatusLabel(status),
                color = when (status) {
                    SetupPermissionStatus.GRANTED -> Primary
                    SetupPermissionStatus.DENIED -> MaterialTheme.colorScheme.error
                    SetupPermissionStatus.NOT_REQUIRED -> TextSecondary
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            status == SetupPermissionStatus.GRANTED || status == SetupPermissionStatus.NOT_REQUIRED -> {
                Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
            }
            actionLabel != null && enabled -> {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.tvFocusable().tvFocusRing(cornerRadius = 12.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun permissionStatusLabel(status: SetupPermissionStatus): String = when (status) {
    SetupPermissionStatus.GRANTED -> stringResource(R.string.onboarding_perm_status_granted)
    SetupPermissionStatus.DENIED -> stringResource(R.string.onboarding_perm_status_denied)
    SetupPermissionStatus.NOT_REQUIRED -> stringResource(R.string.onboarding_perm_status_not_required)
}
