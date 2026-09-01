package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.R
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Verwaltet alle Streaming-Provider — delegates to [ProviderRegistry].
 */
object ProviderManager {

    private val Context.providerDataStore by preferencesDataStore("provider_prefs")
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")

    val providers: List<StreamingProvider> get() = ProviderRegistry.providers

    val defaultProvider: StreamingProvider get() = ProviderRegistry.defaultProvider

    fun activeProviderIdFlow(context: Context): Flow<String> =
        context.providerDataStore.data.map { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] ?: defaultProvider.id
        }

    suspend fun setActiveProvider(context: Context, providerId: String) {
        context.providerDataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] = providerId
        }
    }

    fun getProvider(id: String): StreamingProvider = ProviderRegistry.getProvider(id)

    fun getProviderOrNull(id: String): StreamingProvider? = ProviderRegistry.getProviderOrNull(id)

    fun isValidProviderId(id: String): Boolean = ProviderRegistry.isValidProviderId(id)

    fun getProviderIds(): List<String> = providers.map { it.id }

    fun getProviderInfos(): List<ProviderInfo> = ProviderRegistry.getProviderInfos()

    fun getProviderInfosGroupedByLanguage(): Map<ContentLanguage, List<ProviderInfo>> =
        ProviderRegistry.getGroupedByLanguage()

    fun getFilteredProviderInfos(
        language: ContentLanguage? = null,
        favoriteIds: Set<String> = emptySet(),
        favoritesOnly: Boolean = false
    ): List<ProviderInfo> = ProviderRegistry.getFiltered(language, favoriteIds, favoritesOnly)

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

    fun contentLanguageOf(providerId: String): ContentLanguage =
        ProviderRegistry.contentLanguageOf(providerId)
}

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val supportsSeries: Boolean,
    val supportsMovies: Boolean = !supportsSeries,
    val catalogHint: String? = null,
    val capabilities: ProviderCapabilities = ProviderCapabilities(),
    val contentLanguage: ContentLanguage = ContentLanguage.MULTI,
    val regionLabel: String? = null,
    val logoUrl: String? = null
) {
    val hostLabel: String
        get() = try {
            java.net.URI(baseUrl).host ?: baseUrl
        } catch (_: Exception) {
            baseUrl
        }

    val languageTag: String get() = contentLanguage.tag

    val contentLabelKey: String
        get() = when {
            supportsSeries && supportsMovies -> "provider_content_series_movies"
            supportsMovies -> "provider_content_movies"
            else -> "provider_content_series"
        }

    fun contentLabelRes(): Int = when (contentLabelKey) {
        "provider_content_series_movies" -> R.string.provider_content_series_movies
        "provider_content_movies" -> R.string.provider_content_movies
        else -> R.string.provider_content_series
    }

    fun contentLabel(context: Context): String = context.getString(contentLabelRes())
}
