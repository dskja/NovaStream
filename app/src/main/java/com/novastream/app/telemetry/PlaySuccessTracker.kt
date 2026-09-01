package com.novastream.app.telemetry

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opt-in play success rate telemetry (v12).
 * Stores aggregate counts locally in DataStore — no network upload by default.
 */
private val Context.playTelemetryStore by preferencesDataStore("play_telemetry")

object PlaySuccessTracker {

    private val ENABLED = booleanPreferencesKey("telemetry_enabled")
    private const val TOTAL_PREFIX = "total_"
    private const val SUCCESS_PREFIX = "success_"

    private val mutex = Mutex()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isEnabledFlow(context: Context): Flow<Boolean> =
        context.playTelemetryStore.data.map { it[ENABLED] ?: false }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.playTelemetryStore.edit { it[ENABLED] = enabled }
    }

    suspend fun isEnabled(context: Context): Boolean =
        context.playTelemetryStore.data.map { it[ENABLED] ?: false }.first()

    suspend fun recordSuccess(extractorName: String, @Suppress("UNUSED_PARAMETER") hosterName: String) {
        val ctx = appContext ?: return
        if (!isEnabled(ctx)) return
        recordEvent(ctx, extractorName, success = true)
    }

    suspend fun recordFailure(extractorName: String, @Suppress("UNUSED_PARAMETER") hosterName: String) {
        val ctx = appContext ?: return
        if (!isEnabled(ctx)) return
        recordEvent(ctx, extractorName, success = false)
    }

    private suspend fun recordEvent(context: Context, hoster: String, success: Boolean) {
        val key = hoster.lowercase().take(64)
        mutex.withLock {
            context.playTelemetryStore.edit { prefs ->
                val totalKey = intPreferencesKey("$TOTAL_PREFIX$key")
                val successKey = intPreferencesKey("$SUCCESS_PREFIX$key")
                val total = (prefs[totalKey] ?: 0) + 1
                val successes = (prefs[successKey] ?: 0) + if (success) 1 else 0
                prefs[totalKey] = total
                prefs[successKey] = successes
            }
        }
    }

    suspend fun getSuccessRates(context: Context): Map<String, Float> {
        val prefs = context.playTelemetryStore.data.first()
        return prefs.asMap().keys
            .filterIsInstance<androidx.datastore.preferences.core.Preferences.Key<*>>()
            .mapNotNull { k ->
                val name = k.name
                if (!name.startsWith(TOTAL_PREFIX)) return@mapNotNull null
                val hoster = name.removePrefix(TOTAL_PREFIX)
                val total = prefs[intPreferencesKey(name)] ?: 0
                val success = prefs[intPreferencesKey("$SUCCESS_PREFIX$hoster")] ?: 0
                if (total == 0) null else hoster to success.toFloat() / total
            }
            .toMap()
    }

    suspend fun clearStats(context: Context) {
        context.playTelemetryStore.edit { it.clear() }
    }
}
