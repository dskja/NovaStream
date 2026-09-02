package com.novastream.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.provider.ProviderRegistry
import com.novastream.app.util.VoeWebViewResolver
import com.novastream.app.util.CaptchaWebViewFetcher
import com.novastream.app.data.repository.CatalogCachePurgeWorker
import com.novastream.app.data.repository.NovaStreamRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.novastream.app.util.AppContext
import com.novastream.app.util.LocaleManager
import com.novastream.app.util.PrefsCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class NovaStreamApp : Application(), ImageLoaderFactory {

    @Inject lateinit var database: NovaStreamDatabase
    @Inject lateinit var providerController: ProviderController
    @Inject lateinit var downloadHelper: com.novastream.app.download.DownloadManagerHelper

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        ProviderRegistry.bindContext(base)
        super.attachBaseContext(LocaleManager.wrap(base, PrefsCache.uiLocale(base)))
    }

    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        com.novastream.app.telemetry.PlaySuccessTracker.init(this)
        com.novastream.app.download.DownloadForegroundService.ensureChannel(this)
        appScope.launch {
            runCatching { downloadHelper.resumeDownloads() }
        }
        runCatching { com.novastream.app.cast.CastHelper.get(this) }
        VoeWebViewResolver.setContext(this)
        CaptchaWebViewFetcher.setContext(this)

        // Sync preference cache + build provider registry off the main thread.
        appScope.launch {
            try {
                val settings = com.novastream.app.data.prefs.AppSettings(this@NovaStreamApp)
                PrefsCache.setUiLocale(this@NovaStreamApp, settings.uiLocale.first())
                PrefsCache.setContentLanguage(this@NovaStreamApp, settings.contentLanguage.first())
                ProviderRegistry.ensureBuilt()
                providerController.startObserving(this)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("NovaStreamApp", "Provider registry warmup failed", e)
                }
            }
        }

        appScope.launch {
            try {
                val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val count = database.watchProgressDao().deleteOldCompleted(cutoff)
                if (BuildConfig.DEBUG && count > 0) {
                    android.util.Log.i("NovaStreamApp", "Cleaned up $count old completed episodes")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.w("NovaStreamApp", "Cleanup failed", e)
            }
        }
        appScope.launch {
            runCatching { NovaStreamRepository.get(this@NovaStreamApp).purgeExpiredCache() }
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
        coilSingleton?.memoryCache?.clear()
        CaptchaWebViewFetcher.clear()
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
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }
            .build().also { coilSingleton = it }
    }
}
