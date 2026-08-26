package com.novastream.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.novastream.app.data.api.NetworkModule

class NovaStreamApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkModule.okHttpClient)
            .crossfade(false)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.40)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
