package com.novastream.app.data.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Verwaltet die verfügbaren Streaming-Provider und die aktive Auswahl.
 * Persistiert die Auswahl in DataStore.
 */
object ProviderManager {

    private val Context.providerDataStore by preferencesDataStore("provider_prefs")
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")

    /** Alle registrierten Provider (lazy - wird nur einmal erstellt). */
    val providers: List<StreamingProvider> by lazy {
        listOf(
            SerienStreamProvider(),
            AniWorldProvider(),
            KinoGerProvider(),
            BurningSeriesProvider(),
            MegaKinoProvider(),
            StreamKisteProvider()
        )
    }

    /** Default Provider (lazy). */
    val defaultProvider: StreamingProvider by lazy { providers.first() }

    /** Flow der aktiven Provider-ID. */
    fun activeProviderIdFlow(context: Context): Flow<String> {
        return context.providerDataStore.data.map { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] ?: defaultProvider.id
        }
    }

    /** Setzt den aktiven Provider. */
    suspend fun setActiveProvider(context: Context, providerId: String) {
        context.providerDataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] = providerId
        }
    }

    /** Holt den Provider anhand der ID. */
    fun getProvider(id: String): StreamingProvider =
        providers.find { it.id == id } ?: defaultProvider

    /** Holt den Provider anhand der ID oder null wenn nicht gefunden. */
    fun getProviderOrNull(id: String): StreamingProvider? =
        providers.find { it.id == id }

    /** True wenn ein Provider mit der gegebenen ID existiert. */
    fun isValidProviderId(id: String): Boolean =
        providers.any { it.id == id }

    /** Holt alle Provider-IDs. */
    fun getProviderIds(): List<String> = providers.map { it.id }

    /** Holt alle Provider-Infos für die UI. */
    fun getProviderInfos(): List<ProviderInfo> = providers.map {
        ProviderInfo(
            id = it.id,
            displayName = it.displayName,
            baseUrl = it.baseUrl,
            supportsSeries = it.supportsSeries
        )
    }

    /** Holt den Provider anhand der Display-Name (case-insensitive). */
    fun getProviderByName(displayName: String): StreamingProvider? =
        providers.find { it.displayName.equals(displayName, ignoreCase = true) }

    /** Holt den Provider anhand der Base-URL. */
    fun getProviderByBaseUrl(baseUrl: String): StreamingProvider? =
        providers.find { it.baseUrl == baseUrl }

    /** Anzahl der registrierten Provider. */
    val providerCount: Int get() = providers.size

    /** True wenn mehr als ein Provider verfügbar ist. */
    val hasMultipleProviders: Boolean get() = providers.size > 1

    /** Holt alle Provider-Display-Namen. */
    fun getProviderDisplayNames(): List<String> = providers.map { it.displayName }

    /** Holt die Default-Provider-ID. */
    val defaultProviderId: String get() = defaultProvider.id
}

/** UI-Repräsentation eines Providers. */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val supportsSeries: Boolean
)
