package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.db.CatalogCacheDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central provider switching: holds [activeProviderId] as StateFlow,
 * delegates to [ActiveProvider] and invalidates catalog cache on switch.
 */
@Singleton
class ProviderController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogCacheDao: CatalogCacheDao
) {
    private val _activeProviderId = MutableStateFlow(ProviderManager.defaultProviderId)
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

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
        if (_activeProviderId.value == resolved.id && !_isSwitching.value) return
        _isSwitching.value = true
        try {
            withContext(Dispatchers.IO) {
                val previousId = _activeProviderId.value
                if (previousId != resolved.id) {
                    catalogCacheDao.deleteForProvider(previousId)
                    ProviderDomainResolver.invalidate(previousId)
                    ProviderHttp.clearCache()
                }
                ProviderManager.setActiveProvider(context, resolved.id)
            }
            ActiveProvider.setById(resolved.id)
            _activeProviderId.value = resolved.id
        } finally {
            _isSwitching.value = false
        }
    }
}
