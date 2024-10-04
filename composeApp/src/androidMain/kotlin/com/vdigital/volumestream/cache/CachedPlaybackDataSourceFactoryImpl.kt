package com.vdigital.volumestream.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vdigital.volumestream.AndroidApp
import java.io.File

@OptIn(UnstableApi::class)
class CachedPlaybackDataSourceFactoryImpl(
    private val context: Context ,
) : CachedPlaybackDataSourceFactory {
    lateinit var simpleCache:SimpleCache
    override fun buildCacheDataSourceFactory(): DefaultMediaSourceFactory {
        simpleCache = SimpleCache(
            File(context.cacheDir, CACHE_FILE), LeastRecentlyUsedCacheEvictor(CACHE_SIZE), StandaloneDatabaseProvider(context)
        )
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            CacheDataSource.Factory().setCache(simpleCache)
                .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory()) // Use HTTP for upstream
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // Optional: Ignore cache on error
        )
        return DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }

    override fun clearCache() {
       simpleCache.release()
    }

    companion object {
        const val CACHE_SIZE = 100L * 1024L * 1024L //100 MB
        const val CACHE_FILE = "media"
    }
}