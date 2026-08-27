package com.novastream.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ProviderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NovaStreamApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Load saved provider preference on app start
        appScope.launch {
            // Erste Emission abwarten damit ActiveProvider sofort gesetzt wird
            // (ViewModels könnten sonst den Default Provider nutzen)
            val firstProviderId = ProviderManager.activeProviderIdFlow(this@NovaStreamApp).first()
            ActiveProvider.setById(firstProviderId)
            // Danach weiter collectieren für Änderungen
            ProviderManager.activeProviderIdFlow(this@NovaStreamApp).collect { providerId ->
                ActiveProvider.setById(providerId)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkModule.okHttpClient)
            .crossfade(200)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)  // 30% of app memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(500L * 1024 * 1024)  // 500MB disk cache
                    .build()
            }
            .build()
    }
}
