package com.novastream.app.data.provider

import java.util.Locale

object ProviderLanguageManager {

    fun getAvailableLanguages(): List<ContentLanguage> =
        ProviderRegistry.allRegistered()
            .map { it.contentLanguage }
            .distinct()
            .sortedBy { it.tag }

    fun getProvidersForLanguage(language: ContentLanguage): List<StreamingProvider> =
        ProviderRegistry.allRegistered()
            .filter { it.contentLanguage == language || it.contentLanguage == ContentLanguage.MULTI }
            .map { it.provider }

    fun getLanguageDisplayName(language: ContentLanguage, locale: Locale = Locale.getDefault()): String {
        if (language == ContentLanguage.MULTI) return "Multi"
        return Locale.forLanguageTag(language.tag)
            .getDisplayLanguage(locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    fun getProvidersGroupedByLanguage(): Map<ContentLanguage, List<ProviderInfo>> =
        ProviderRegistry.getGroupedByLanguage()
}
