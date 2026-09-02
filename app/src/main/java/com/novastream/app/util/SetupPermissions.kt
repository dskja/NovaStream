package com.novastream.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

enum class SetupPermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUIRED
}

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

fun notificationPermissionStatus(context: Context): SetupPermissionStatus =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> SetupPermissionStatus.NOT_REQUIRED
        hasNotificationPermission(context) -> SetupPermissionStatus.GRANTED
        else -> SetupPermissionStatus.DENIED
    }

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun batteryOptimizationStatus(context: Context): SetupPermissionStatus =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> SetupPermissionStatus.NOT_REQUIRED
        isIgnoringBatteryOptimizations(context) -> SetupPermissionStatus.GRANTED
        else -> SetupPermissionStatus.DENIED
    }

/** Network access is a normal permission — granted at install time. */
fun networkPermissionStatus(): SetupPermissionStatus = SetupPermissionStatus.GRANTED

fun isTvDevice(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

fun openBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val packageIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(packageIntent) }.isFailure) {
        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(listIntent) }
    }
}

@Composable
fun rememberNotificationPermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return remember { { onResult(true) } }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    return remember(launcher) { { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}

/**
 * Requests POST_NOTIFICATIONS on Android 13+ before starting a foreground playback service.
 */
@Composable
fun RememberPlaybackNotificationPermission(onGranted: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onGranted() }
        return
    }

    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requested = true
        onGranted()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!requested) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
