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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val providerController = onboardingVm.providerController
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var uiLocale by remember { mutableStateOf(LocaleManager.SYSTEM_LOCALE) }
    var contentLanguage by remember { mutableStateOf(ContentLanguage.DE) }
    var selectedId by remember { mutableStateOf(ProviderManager.defaultProviderId) }
    var languageFilter by remember { mutableStateOf<ContentLanguage?>(null) }
    val initialFocus = rememberInitialFocusRequester()

    val grouped = remember(languageFilter) {
        val favorites = emptySet<String>()
        if (languageFilter != null) {
            mapOf(languageFilter!! to ProviderManager.getFilteredProviderInfos(language = languageFilter, favoriteIds = favorites))
        } else {
            ProviderManager.getProviderInfosGroupedByLanguage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgPure)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.onboarding_welcome),
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (step) {
                0 -> stringResource(R.string.onboarding_ui_language)
                1 -> stringResource(R.string.onboarding_content_language)
                else -> stringResource(R.string.onboarding_provider_hint)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> {
                (listOf(LocaleManager.SYSTEM_LOCALE) + LocaleManager.supportedUiLocales).forEachIndexed { index, tag ->
                    val selected = uiLocale == tag
                    SelectionRow(
                        title = LocaleManager.localeDisplayName(tag),
                        selected = selected,
                        isFirst = index == 0,
                        initialFocus = initialFocus,
                        onClick = { uiLocale = tag }
                    )
                }
            }
            1 -> {
                ContentLanguage.entries.filter { it != ContentLanguage.MULTI }.forEachIndexed { index, lang ->
                    val selected = contentLanguage == lang
                    SelectionRow(
                        title = ProviderLanguageManager.getLanguageDisplayName(lang),
                        selected = selected,
                        isFirst = index == 0,
                        initialFocus = initialFocus,
                        onClick = {
                            contentLanguage = lang
                            languageFilter = lang
                        }
                    )
                }
            }
            else -> {
                Text(
                    stringResource(R.string.onboarding_provider),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                ProviderLanguageFilterChips(
                    selectedLanguage = languageFilter,
                    favoritesOnly = false,
                    onLanguageSelected = { languageFilter = it },
                    onFavoritesToggle = { }
                )
                grouped.forEach { (lang, providers) ->
                    ProviderLanguageSectionHeader(lang, providers.size)
                    providers.forEach { providerInfo ->
                        ProviderSelectionRow(
                            providerInfo = providerInfo,
                            selected = providerInfo.id == selectedId,
                            context = context,
                            onClick = { selectedId = providerInfo.id }
                        )
                    }
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
                        when (step) {
                            0 -> step = 1
                            1 -> {
                                appSettings.setUiLocale(uiLocale)
                                appSettings.setContentLanguage(contentLanguage.tag)
                                step = 2
                            }
                            else -> {
                                providerController.setActiveProvider(selectedId)
                                appSettings.setOnboardingComplete(true)
                                onComplete()
                            }
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (step < 2) stringResource(R.string.onboarding_continue) else stringResource(R.string.onboarding_start),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_disclaimer),
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SelectionRow(
    title: String,
    selected: Boolean,
    isFirst: Boolean,
    initialFocus: androidx.compose.ui.focus.FocusRequester,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Primary.copy(alpha = 0.12f) else BgSurface)
            .then(
                if (isFirst) Modifier.tvFocusable(focusRequester = initialFocus).tvFocusRing(cornerRadius = 16.dp)
                else Modifier.tvFocusable().tvFocusRing(cornerRadius = 16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (selected) Primary.copy(alpha = 0.2f) else BgSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.Stream,
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
    context: Context,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Primary.copy(alpha = 0.12f) else BgSurface)
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
