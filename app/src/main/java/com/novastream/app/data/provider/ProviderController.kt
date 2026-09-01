package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.db.CatalogCacheDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zentraler Provider-Wechsel: hält [activeProviderId] als StateFlow,
 * delegiert an [ActiveProvider] und invalidiert den Katalog-Cache beim Wechsel.
 */
@Singleton
class ProviderController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogCacheDao: CatalogCacheDao
) {
    private val _activeProviderId = MutableStateFlow(ProviderManager.defaultProviderId)
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            try {
                ProviderManager.activeProviderIdFlow(context).collect { providerId ->
                    syncFromStore(providerId)
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) {
                    android.util.Log.e("ProviderController", "Provider sync failed", e)
                }
                syncFromStore(ProviderManager.defaultProviderId)
            }
        }
    }

    private fun syncFromStore(providerId: String) {
        ActiveProvider.setById(providerId)
        _activeProviderId.value = providerId
    }

    suspend fun setActiveProvider(providerId: String) {
        val resolved = ProviderManager.getProviderOrNull(providerId) ?: return
        val previousId = _activeProviderId.value
        if (previousId != resolved.id) {
            catalogCacheDao.deleteForProvider(previousId)
        }
        ProviderManager.setActiveProvider(context, resolved.id)
        ActiveProvider.setById(resolved.id)
        _activeProviderId.value = resolved.id
    }
}
