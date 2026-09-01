package com.novastream.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.util.VoeWebViewResolver
import com.novastream.app.util.CaptchaWebViewFetcher
import com.novastream.app.data.repository.CatalogCachePurgeWorker
import com.novastream.app.data.repository.NovaStreamRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltAndroidApp
class NovaStreamApp : Application(), ImageLoaderFactory {

    @Inject lateinit var database: NovaStreamDatabase
    @Inject lateinit var providerController: ProviderController

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Set VoeWebViewResolver context for VOE hoster resolution
        VoeWebViewResolver.setContext(this)
        CaptchaWebViewFetcher.setContext(this)
        providerController.startObserving(appScope)
        // Cleanup: Entferne abgeschlossene Episoden die älter als 30 Tage sind
        appScope.launch {
            try {
                val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val count = database.watchProgressDao().deleteOldCompleted(cutoff)
                if (com.novastream.app.BuildConfig.DEBUG && count > 0) {
                    android.util.Log.i("NovaStreamApp", "Cleaned up $count old completed episodes")
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("NovaStreamApp", "Cleanup failed", e)
            }
        }
        appScope.launch {
            NovaStreamRepository.get(this@NovaStreamApp).purgeExpiredCache()
        }
        CatalogCachePurgeWorker.schedule(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
        VoeWebViewResolver.clearContext()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Clear Coil memory cache on low memory
        coilSingleton?.memoryCache?.clear()
    }

    private var coilSingleton: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkModule.imageOkHttpClient)
            .crossfade(200)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .components {
                add(object : coil.intercept.Interceptor {
                    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                        val request = chain.request
                        val referer = com.novastream.app.util.MediaUrls.refererFor(request.data.toString())
                        val newRequest = request.newBuilder()
                            .setHeader("Referer", referer)
                            .setHeader("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                            .build()
                        return chain.proceed(newRequest)
                    }
                })
            }
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
            .build().also { coilSingleton = it }
    }
}
