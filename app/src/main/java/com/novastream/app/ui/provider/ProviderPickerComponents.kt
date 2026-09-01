package com.novastream.app.ui.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novastream.app.R
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderInfo
import com.novastream.app.data.provider.ProviderLanguageManager
import com.novastream.app.ui.theme.TextPrimary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderLanguageFilterChips(
    selectedLanguage: ContentLanguage?,
    favoritesOnly: Boolean,
    onLanguageSelected: (ContentLanguage?) -> Unit,
    onFavoritesToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedLanguage == null && !favoritesOnly,
            onClick = {
                onFavoritesToggle(false)
                onLanguageSelected(null)
            },
            label = { Text(stringResource(R.string.provider_filter_all)) }
        )
        ProviderLanguageManager.getAvailableLanguages()
            .filter { it != ContentLanguage.MULTI }
            .forEach { lang ->
                FilterChip(
                    selected = selectedLanguage == lang && !favoritesOnly,
                    onClick = {
                        onFavoritesToggle(false)
                        onLanguageSelected(lang)
                    },
                    label = {
                        Text(
                            ProviderLanguageManager.getLanguageDisplayName(lang, locale).uppercase(locale)
                        )
                    }
                )
            }
        FilterChip(
            selected = favoritesOnly,
            onClick = { onFavoritesToggle(!favoritesOnly) },
            label = { Text(stringResource(R.string.provider_filter_favorites)) },
            leadingIcon = {
                Icon(
                    if (favoritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
fun ProviderLanguageSectionHeader(
    language: ContentLanguage,
    count: Int,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    Text(
        text = stringResource(
            R.string.provider_section_header,
            ProviderLanguageManager.getLanguageDisplayName(language, locale),
            count
        ),
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun providerContentLabel(info: ProviderInfo): String {
    val context = LocalContext.current
    return info.contentLabel(context)
}
