package com.novastream.app.ui.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.rememberInitialFocusRequester
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    val providers = remember { ProviderManager.getProviderInfos() }
    var selectedId by remember { mutableStateOf(ProviderManager.defaultProviderId) }
    val initialFocus = rememberInitialFocusRequester()

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Willkommen bei NovaStream",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Wähle deinen Streaming-Provider. Du kannst ihn später in den Einstellungen ändern.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))

        providers.forEachIndexed { index, providerInfo ->
            val isSelected = providerInfo.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Primary.copy(alpha = 0.12f) else BgSurface)
                    .then(
                        if (index == 0) {
                            Modifier.tvFocusable(focusRequester = initialFocus).tvFocusRing(cornerRadius = 16.dp)
                        } else {
                            Modifier.tvFocusable().tvFocusRing(cornerRadius = 16.dp)
                        }
                    )
                    .clickable { selectedId = providerInfo.id }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Primary.copy(alpha = 0.2f) else BgSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isSelected) Icons.Default.Check else Icons.Default.Stream,
                        contentDescription = null,
                        tint = if (isSelected) Primary else TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        providerInfo.displayName,
                        color = TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${providerInfo.contentLabel} · ${providerInfo.hostLabel}",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PrimaryGradient)
                .tvFocusable()
                .tvFocusRing(cornerRadius = 24.dp)
                .clickable {
                    scope.launch {
                        ProviderManager.setActiveProvider(context, selectedId)
                        ActiveProvider.setById(selectedId)
                        appSettings.setOnboardingComplete(true)
                        onComplete()
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Los geht's",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "NovaStream ist ein inoffizieller Client – nur für Bildungszwecke.",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
