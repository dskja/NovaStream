package com.novastream.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.util.PrefsCache
import com.novastream.app.util.LocaleManager

/**
 * App-weite Einstellungen via DataStore.
 * Speichert User-Präferenzen wie Autoplay, Playback Speed, Dynamic Color, etc.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettings(private val context: Context) {

    // ─── Keys ───────────────────────────────────────────────────────

    companion object {
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next_episode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_INTRO_BUTTON = booleanPreferencesKey("skip_intro_button")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val UNKNOWN_PROVIDER_CLEANUP_PROMPTED = booleanPreferencesKey("unknown_provider_cleanup_prompted")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val PREFERRED_HOSTER = stringPreferencesKey("preferred_hoster")
        val DEFAULT_SEASON = intPreferencesKey("default_season")
        val DATA_SAVER_MODE = booleanPreferencesKey("data_saver_mode")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        val UI_LOCALE = stringPreferencesKey("ui_locale")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val PLAY_TELEMETRY = booleanPreferencesKey("play_telemetry_enabled")
        val IPTV_ENABLED = booleanPreferencesKey("iptv_enabled")
        val USER_M3U_URL = stringPreferencesKey("user_m3u_url")
        val EPG_URL = stringPreferencesKey("epg_xmltv_url")
        val SYNC_URL = stringPreferencesKey("sync_url")
        val SYNC_DEVICE_KEY = stringPreferencesKey("sync_device_key")
        val CAST_ENABLED = booleanPreferencesKey("cast_enabled")
    }

    // ─── Flows ──────────────────────────────────────────────────────

    val autoplayNext: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY_NEXT] ?: true }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[PLAYBACK_SPEED] ?: 1.0f }
    val skipIntroButton: Flow<Boolean> = context.dataStore.data.map { it[SKIP_INTRO_BUTTON] ?: true }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val unknownProviderCleanupPrompted: Flow<Boolean> =
        context.dataStore.data.map { it[UNKNOWN_PROVIDER_CLEANUP_PROMPTED] ?: false }
    val preferredLanguage: Flow<String> = context.dataStore.data.map { it[PREFERRED_LANGUAGE] ?: "Deutsch" }
    val preferredHoster: Flow<String> = context.dataStore.data.map { it[PREFERRED_HOSTER] ?: "VOE" }
    val defaultSeason: Flow<Int> = context.dataStore.data.map { it[DEFAULT_SEASON] ?: 1 }
    val dataSaverMode: Flow<Boolean> = context.dataStore.data.map { it[DATA_SAVER_MODE] ?: false }
    val reduceMotion: Flow<Boolean> = context.dataStore.data.map { it[REDUCE_MOTION] ?: false }
    val performanceMode: Flow<Boolean> = context.dataStore.data.map { it[PERFORMANCE_MODE] ?: false }
    val uiLocale: Flow<String> = context.dataStore.data.map { it[UI_LOCALE] ?: LocaleManager.SYSTEM_LOCALE }
    val contentLanguage: Flow<String> = context.dataStore.data.map { it[CONTENT_LANGUAGE] ?: ContentLanguage.DE.tag }
    val playTelemetry: Flow<Boolean> = context.dataStore.data.map { it[PLAY_TELEMETRY] ?: false }
    val iptvEnabled: Flow<Boolean> = context.dataStore.data.map { it[IPTV_ENABLED] ?: false }
    val userM3uUrl: Flow<String> = context.dataStore.data.map { it[USER_M3U_URL].orEmpty() }
    val epgUrl: Flow<String> = context.dataStore.data.map { it[EPG_URL].orEmpty() }
    val syncUrl: Flow<String> = context.dataStore.data.map { it[SYNC_URL].orEmpty() }
    val syncDeviceKey: Flow<String> = context.dataStore.data.map { it[SYNC_DEVICE_KEY].orEmpty() }
    val castEnabled: Flow<Boolean> = context.dataStore.data.map { it[CAST_ENABLED] ?: true }

    // ─── Setters ────────────────────────────────────────────────────

    suspend fun setAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { it[AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[PLAYBACK_SPEED] = speed.coerceIn(0.25f, 4.0f) }
    }

    suspend fun setSkipIntroButton(enabled: Boolean) {
        context.dataStore.edit { it[SKIP_INTRO_BUTTON] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setUnknownProviderCleanupPrompted(prompted: Boolean) {
        context.dataStore.edit { it[UNKNOWN_PROVIDER_CLEANUP_PROMPTED] = prompted }
    }

    suspend fun setPreferredLanguage(language: String) {
        context.dataStore.edit { it[PREFERRED_LANGUAGE] = language }
    }

    suspend fun setPreferredHoster(hoster: String) {
        context.dataStore.edit { it[PREFERRED_HOSTER] = hoster }
    }

    suspend fun setDefaultSeason(season: Int) {
        context.dataStore.edit { it[DEFAULT_SEASON] = season.coerceAtLeast(1) }
    }

    suspend fun setDataSaverMode(enabled: Boolean) {
        context.dataStore.edit { it[DATA_SAVER_MODE] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[REDUCE_MOTION] = enabled }
    }

    suspend fun setPerformanceMode(enabled: Boolean) {
        context.dataStore.edit { it[PERFORMANCE_MODE] = enabled }
    }

    suspend fun setUiLocale(localeTag: String) {
        PrefsCache.setUiLocale(context, localeTag)
        context.dataStore.edit { it[UI_LOCALE] = localeTag }
    }

    suspend fun setContentLanguage(tag: String) {
        PrefsCache.setContentLanguage(context, tag)
        context.dataStore.edit { it[CONTENT_LANGUAGE] = tag }
    }

    suspend fun setPlayTelemetry(enabled: Boolean) {
        context.dataStore.edit { it[PLAY_TELEMETRY] = enabled }
    }

    suspend fun setIptvEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IPTV_ENABLED] = enabled }
    }

    suspend fun setUserM3uUrl(url: String) {
        context.dataStore.edit { it[USER_M3U_URL] = url.trim() }
    }

    suspend fun setEpgUrl(url: String) {
        context.dataStore.edit { it[EPG_URL] = url.trim() }
    }

    suspend fun setSyncUrl(url: String) {
        context.dataStore.edit { it[SYNC_URL] = url.trim() }
    }

    suspend fun setSyncDeviceKey(key: String) {
        context.dataStore.edit { it[SYNC_DEVICE_KEY] = key.trim() }
    }

    suspend fun setCastEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CAST_ENABLED] = enabled }
    }
}
