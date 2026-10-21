package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class for Munowatch Android TV.
 * Configures an ultra-lightweight image loading engine and memory bounds
 * tailored for low-RAM (1GB - 1.5GB) Android TV chipsets.
 */
class MunowatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Dedicated bounded OkHttpClient for smooth asynchronous poster image loading without UI thread contention
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 6
            })
            .connectionPool(okhttp3.ConnectionPool(16, 2, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        // Configure an optimized Coil ImageLoader with dedicated background IO dispatchers
        // to completely eliminate UI freezes and dropped frames on Android TV devices
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .fetcherDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .decoderDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .transformationDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Balanced 25% heap cap for smooth poster caching
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB disk cache
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 16-bit RGB saves 50% memory compared to ARGB_8888
            .allowRgb565(true)
            .crossfade(false) // Disable alpha blending to prevent GPU fill-rate thrashing
            .respectCacheHeaders(false)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
