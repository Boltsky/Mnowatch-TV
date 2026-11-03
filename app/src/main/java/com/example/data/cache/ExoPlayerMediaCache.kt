package com.example.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object ExoPlayerMediaCache {
    private const val CACHE_DIR_NAME = "exoplayer_media_cache"
    // 2 GB LRU disk cache budget
    const val DEFAULT_MAX_BYTES = 2L * 1024L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    private val lock = Any()

    fun getCache(context: Context, maxSizeBytes: Long = DEFAULT_MAX_BYTES): SimpleCache {
        simpleCache?.let { return it }
        return synchronized(lock) {
            simpleCache ?: run {
                val appContext = context.applicationContext
                val dbProvider = databaseProvider ?: StandaloneDatabaseProvider(appContext).also {
                    databaseProvider = it
                }
                val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val evictor = LeastRecentlyUsedCacheEvictor(maxSizeBytes)
                val cache = SimpleCache(cacheDir, evictor, dbProvider)
                simpleCache = cache
                cache
            }
        }
    }

    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory,
        maxSizeBytes: Long = DEFAULT_MAX_BYTES
    ): CacheDataSource.Factory {
        val cache = getCache(context, maxSizeBytes)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun release() {
        synchronized(lock) {
            try {
                simpleCache?.release()
            } catch (_: Exception) {}
            simpleCache = null
            databaseProvider = null
        }
    }

    fun clearCache(context: Context) {
        synchronized(lock) {
            release()
            try {
                val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }
}
