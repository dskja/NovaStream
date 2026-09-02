package com.novastream.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.novastream.app.data.prefs.AppSettings
import java.util.Locale

object LocaleManager {
    const val SYSTEM_LOCALE = "system"

    val supportedUiLocales: List<String> = listOf("de", "en", "es", "fr", "it", "pl", "ar")

    fun wrap(context: Context, localeTag: String? = null): Context {
        val tag = localeTag ?: PrefsCache.uiLocale(context)
        if (tag == SYSTEM_LOCALE) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun localeDisplayName(tag: String, displayLocale: Locale = Locale.getDefault()): String =
        when (tag) {
            SYSTEM_LOCALE -> "System"
            "de" -> "Deutsch"
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "it" -> "Italiano"
            "pl" -> "Polski"
            "ar" -> "العربية"
            else -> Locale.forLanguageTag(tag).getDisplayLanguage(displayLocale)
        }

    fun isRtl(tag: String): Boolean = when (tag) {
        "ar" -> true
        SYSTEM_LOCALE -> java.util.Locale.getDefault().language == "ar"
        else -> false
    }

    @Composable
    fun ProvideLayoutDirection(uiLocale: String, content: @Composable () -> Unit) {
        val layoutDirection = if (isRtl(uiLocale)) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            content()
        }
    }
}
