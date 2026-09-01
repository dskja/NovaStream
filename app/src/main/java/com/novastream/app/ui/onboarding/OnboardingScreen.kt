package com.novastream.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.R
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.*
import com.novastream.app.ui.provider.ProviderLanguageFilterChips
import com.novastream.app.ui.provider.ProviderLanguageSectionHeader
import com.novastream.app.ui.theme.*
import com.novastream.app.ui.tv.rememberInitialFocusRequester
import com.novastream.app.ui.tv.tvFocusRing
import com.novastream.app.ui.tv.tvFocusable
import com.novastream.app.util.LocaleManager
import kotlinx.coroutines.launch

private const val TOTAL_STEPS = 5

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val providerController = onboardingVm.providerController
    val isSwitching by providerController.isSwitching.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var uiLocale by remember { mutableStateOf(LocaleManager.SYSTEM_LOCALE) }
    var contentLanguage by remember { mutableStateOf(ContentLanguage.DE) }
    var selectedId by remember { mutableStateOf(ProviderManager.defaultProviderId) }
    var languageFilter by remember { mutableStateOf<ContentLanguage?>(ContentLanguage.DE) }
    var finishing by remember { mutableStateOf(false) }
    val initialFocus = rememberInitialFocusRequester()

    val grouped = remember(languageFilter) {
        if (languageFilter != null) {
            mapOf(languageFilter!! to ProviderManager.getFilteredProviderInfos(language = languageFilter, favoriteIds = emptySet()))
        } else {
            ProviderManager.getProviderInfosGroupedByLanguage()
        }
    }

    val flatProviders = remember(grouped) { grouped.values.flatten() }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / TOTAL_STEPS },
            modifier = Modifier.fillMaxWidth(),
            color = Primary,
            trackColor = BgSurfaceElevated
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    when (step) {
                        0 -> stringResource(R.string.onboarding_welcome)
                        1 -> stringResource(R.string.onboarding_ui_language)
                        2 -> stringResource(R.string.onboarding_content_language)
                        3 -> stringResource(R.string.onboarding_provider)
                        else -> stringResource(R.string.onboarding_features_title)
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (step) {
                        0 -> stringResource(R.string.onboarding_welcome_sub)
                        1 -> stringResource(R.string.onboarding_ui_language_sub)
                        2 -> stringResource(R.string.onboarding_content_language_sub)
                        3 -> stringResource(R.string.onboarding_provider_hint)
                        else -> stringResource(R.string.onboarding_features_sub)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(20.dp))
            }

            when (step) {
                0 -> {
                    item {
                        FeatureHighlight(
                            icon = Icons.Default.Stream,
                            title = stringResource(R.string.onboarding_feature_catalog),
                            subtitle = stringResource(R.string.onboarding_feature_catalog_sub)
                        )
                        FeatureHighlight(
                            icon = Icons.Default.Download,
                            title = stringResource(R.string.onboarding_feature_downloads),
                            subtitle = stringResource(R.string.onboarding_feature_downloads_sub)
                        )
                        FeatureHighlight(
                            icon = Icons.Default.LiveTv,
                            title = stringResource(R.string.onboarding_feature_livetv),
                            subtitle = stringResource(R.string.onboarding_feature_livetv_sub)
                        )
                        FeatureHighlight(
                            icon = Icons.Default.Tv,
                            title = stringResource(R.string.onboarding_feature_cast),
                            subtitle = stringResource(R.string.onboarding_feature_cast_sub)
                        )
                    }
                }
                1 -> {
                    items((listOf(LocaleManager.SYSTEM_LOCALE) + LocaleManager.supportedUiLocales)) { tag ->
                        val selected = uiLocale == tag
                        SelectionRow(
                            title = LocaleManager.localeDisplayName(tag),
                            selected = selected,
                            onClick = { uiLocale = tag }
                        )
                    }
                }
                2 -> {
                    items(ContentLanguage.entries.filter { it != ContentLanguage.MULTI }) { lang ->
                        val selected = contentLanguage == lang
                        SelectionRow(
                            title = ProviderLanguageManager.getLanguageDisplayName(lang),
                            selected = selected,
                            onClick = {
                                contentLanguage = lang
                                languageFilter = lang
                            }
                        )
                    }
                }
                3 -> {
                    item {
                        ProviderLanguageFilterChips(
                            selectedLanguage = languageFilter,
                            favoritesOnly = false,
                            onLanguageSelected = { languageFilter = it },
                            onFavoritesToggle = { }
                        )
                    }
                    grouped.forEach { (lang, providers) ->
                        item { ProviderLanguageSectionHeader(lang, providers.size) }
                        items(providers, key = { it.id }) { providerInfo ->
                            ProviderSelectionRow(
                                providerInfo = providerInfo,
                                selected = providerInfo.id == selectedId,
                                context = context,
                                onClick = { selectedId = providerInfo.id }
                            )
                        }
                    }
                }
                else -> {
                    item {
                        Text(
                            stringResource(R.string.onboarding_ready_provider, flatProviders.find { it.id == selectedId }?.displayName ?: selectedId),
                            color = Primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.onboarding_disclaimer),
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(BgPure)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { if (!finishing && !isSwitching) step -= 1 },
                    enabled = !finishing && !isSwitching,
                    modifier = Modifier.tvFocusable().tvFocusRing(cornerRadius = 24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (finishing || isSwitching) BgSurfaceElevated else PrimaryGradient)
                    .tvFocusable(focusRequester = if (step == 0) initialFocus else null)
                    .tvFocusRing(cornerRadius = 24.dp)
                    .clickable(enabled = !finishing && !isSwitching) {
                        scope.launch {
                            when (step) {
                                0 -> step = 1
                                1 -> {
                                    appSettings.setUiLocale(uiLocale)
                                    step = 2
                                }
                                2 -> {
                                    appSettings.setContentLanguage(contentLanguage.tag)
                                    languageFilter = contentLanguage
                                    step = 3
                                }
                                3 -> step = 4
                                else -> {
                                    finishing = true
                                    try {
                                        providerController.setActiveProvider(selectedId)
                                        appSettings.setOnboardingComplete(true)
                                        onComplete()
                                    } finally {
                                        finishing = false
                                    }
                                }
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (finishing || isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        when (step) {
                            TOTAL_STEPS - 1 -> stringResource(R.string.onboarding_start)
                            else -> stringResource(R.string.onboarding_continue)
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlight(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
        Column {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Primary.copy(alpha = 0.12f) else BgSurface)
            .tvFocusable()
            .tvFocusRing(cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (selected) Primary.copy(alpha = 0.2f) else BgSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.Language,
                contentDescription = null,
                tint = if (selected) Primary else TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ProviderSelectionRow(
    providerInfo: ProviderInfo,
    selected: Boolean,
    context: android.content.Context,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Primary.copy(alpha = 0.12f) else BgSurface)
            .tvFocusable()
            .tvFocusRing(cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(providerInfo.displayName, color = TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Text(
                "${providerInfo.contentLabel(context)} · ${providerInfo.hostLabel}",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
    }
}
