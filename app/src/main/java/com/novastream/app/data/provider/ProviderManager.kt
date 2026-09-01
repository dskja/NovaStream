package com.novastream.app.data.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Verwaltet alle Streaming-Provider (SerienStream-Familie + FMHY + Free Catalog).
 */
object ProviderManager {

    private val Context.providerDataStore by preferencesDataStore("provider_prefs")
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")

    val providers: List<StreamingProvider> by lazy {
        listOf(
            // Primär / DE
            SerienStreamProvider(),
            SerienStreamCxProvider(),
            AniWorldProvider(),
            KinoGerProvider(),
            BurningSeriesProvider(),
            MegaKinoProvider(),
            StreamKisteProvider(),
            FilmPalastProvider(),
            KinoZProvider(),
            // AAA Free Catalog (TVMaze, kein Key)
            FreeCatalogProvider(),
            // FMHY / internationale Sites
            HydraHdProvider(),
            CinezoProvider(),
            ShowsStProvider(),
            PhantomFlixProvider(),
            FlixerProvider(),
            DramaCoolProvider(),
            PressPlayProvider()
        )
    }

    val defaultProvider: StreamingProvider by lazy { providers.first() }

    fun activeProviderIdFlow(context: Context): Flow<String> =
        context.providerDataStore.data.map { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] ?: defaultProvider.id
        }

    suspend fun setActiveProvider(context: Context, providerId: String) {
        context.providerDataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] = providerId
        }
    }

    fun getProvider(id: String): StreamingProvider =
        providers.find { it.id == id } ?: defaultProvider

    fun getProviderOrNull(id: String): StreamingProvider? =
        providers.find { it.id == id }

    fun isValidProviderId(id: String): Boolean =
        providers.any { it.id == id }

    fun getProviderIds(): List<String> = providers.map { it.id }

    fun getProviderInfos(): List<ProviderInfo> = providers.map {
        ProviderInfo(
            id = it.id,
            displayName = it.displayName,
            baseUrl = it.baseUrl,
            supportsSeries = it.supportsSeries,
            supportsMovies = it.supportsMovies,
            catalogHint = it.catalogHint,
            capabilities = it.capabilities()
        )
    }

    fun getProviderByName(displayName: String): StreamingProvider? =
        providers.find { it.displayName.equals(displayName, ignoreCase = true) }

    fun getProviderByBaseUrl(baseUrl: String): StreamingProvider? =
        providers.find { it.baseUrl == baseUrl }

    val providerCount: Int get() = providers.size
    val hasMultipleProviders: Boolean get() = providers.size > 1
    fun getProviderDisplayNames(): List<String> = providers.map { it.displayName }
    val defaultProviderId: String get() = defaultProvider.id
    fun seriesProviders(): List<StreamingProvider> = providers.filter { it.supportsSeries }
    fun movieProviders(): List<StreamingProvider> = providers.filter { it.supportsMovies }
}

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val supportsSeries: Boolean,
    val supportsMovies: Boolean = !supportsSeries,
    val catalogHint: String? = null,
    val capabilities: ProviderCapabilities = ProviderCapabilities()
) {
    val hostLabel: String
        get() = try {
            java.net.URI(baseUrl).host ?: baseUrl
        } catch (_: Exception) {
            baseUrl
        }

    val contentLabel: String
        get() = when {
            supportsSeries && supportsMovies -> "Serien & Filme"
            supportsMovies -> "Filme"
            else -> "Serien"
        }
}
