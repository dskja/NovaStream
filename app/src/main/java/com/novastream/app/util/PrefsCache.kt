package com.novastream.app.util

import android.content.Context
import com.novastream.app.data.provider.ContentLanguage

/** Synchronous preference cache for attachBaseContext and provider region resolution. */
object PrefsCache {
    private const val PREFS = "novastream_sync_prefs"
    private const val KEY_UI_LOCALE = "ui_locale"
    private const val KEY_CONTENT_LANGUAGE = "content_language"

    fun uiLocale(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UI_LOCALE, LocaleManager.SYSTEM_LOCALE)
            ?: LocaleManager.SYSTEM_LOCALE

    fun setUiLocale(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UI_LOCALE, tag)
            .apply()
    }

    fun contentLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CONTENT_LANGUAGE, ContentLanguage.DE.tag)
            ?: ContentLanguage.DE.tag

    fun setContentLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONTENT_LANGUAGE, tag)
            .apply()
    }
}
