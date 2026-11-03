package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.size.Precision

/**
 * Application class for Munowatch Android TV.
 * Configures an ultra-lightweight image loading engine and memory bounds
 * tailored for low-RAM (1GB - 1.5GB) Android TV chipsets.
 */
class MunowatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true || Runtime.getRuntime().maxMemory() < 192 * 1024 * 1024

        // Dedicated bounded OkHttpClient for smooth asynchronous poster image loading without UI thread contention.
        // On low-RAM TVs, limits concurrent sockets to 4 to prevent CPU starvation and thread thrashing.
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = if (isLowRam) 4 else 8
                maxRequestsPerHost = if (isLowRam) 2 else 4
            })
            .connectionPool(okhttp3.ConnectionPool(if (isLowRam) 4 else 8, 2, java.util.concurrent.TimeUnit.MINUTES))
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
                    .maxSizePercent(if (isLowRam) 0.10 else 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowRam) 50L * 1024 * 1024 else 100L * 1024 * 1024)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 16-bit RGB saves 50% memory compared to ARGB_8888
            .allowRgb565(true)
            .precision(Precision.INEXACT)
            .crossfade(false) // Disable alpha blending to prevent GPU fill-rate thrashing
            .respectCacheHeaders(false)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
