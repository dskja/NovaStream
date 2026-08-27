package com.novastream.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    }

    // ─── Flows ──────────────────────────────────────────────────────

    val autoplayNext: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY_NEXT] ?: true }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[PLAYBACK_SPEED] ?: 1.0f }
    val skipIntroButton: Flow<Boolean> = context.dataStore.data.map { it[SKIP_INTRO_BUTTON] ?: true }

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
}
