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
import com.vdigital.volumestream.config.PlayerConfig
import java.io.File

@OptIn(UnstableApi::class)
class CachedPlaybackDataSourceFactoryImpl(
    private val context: Context,
    private val playerConfig: PlayerConfig = PlayerConfig(),
) : CachedPlaybackDataSourceFactory {

    private var simpleCache: SimpleCache? = null
    private var cacheReleased = true

    /**
     * Persistent HTTP factory shared across all [buildCacheDataSourceFactory] calls.
     *
     * Keeping this as a field — rather than recreating it inside
     * [buildCacheDataSourceFactory] — means [setDefaultHeaders] can mutate the
     * factory's request properties in-place.  ExoPlayer holds a reference to this
     * exact instance through the CacheDataSource chain, so any header update
     * (e.g. a new session token after a track switch) is picked up immediately for
     * all subsequent segment / key requests without rebuilding the player.
     */
    private val httpFactory = DefaultHttpDataSource.Factory()

    override fun setDefaultHeaders(headers: Map<String, String>) {
        // Update the live factory instance so already-running ExoPlayer sessions
        // adopt the new headers on the very next data-source creation (new segment).
        httpFactory.setDefaultRequestProperties(headers)
    }

    /** Returns the existing cache or creates a fresh one if it was released. */
    private fun requireCache(): SimpleCache {
        if (simpleCache == null || cacheReleased) {
            simpleCache = SimpleCache(
                File(context.cacheDir, playerConfig.cacheDirName),
                LeastRecentlyUsedCacheEvictor(playerConfig.cacheSizeBytes),
                StandaloneDatabaseProvider(context)
            )
            cacheReleased = false
        }
        return simpleCache!!
    }

    override fun buildCacheDataSourceFactory(): DefaultMediaSourceFactory {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            CacheDataSource.Factory()
                .setCache(requireCache())
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        )
        return DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }

    override fun clearCache() {
        simpleCache?.release()
        simpleCache = null
        cacheReleased = true
    }
}
