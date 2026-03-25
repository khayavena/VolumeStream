package com.vdigital.volumestream.cache

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
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

    /** Live HTTP factory — headers updated via [setDefaultHeaders] without rebuilding ExoPlayer. */
    private val httpFactory = DefaultHttpDataSource.Factory()

    /** AES-128 session key; null until [setAesKey] is called after session start. */
    @Volatile private var aesKey: ByteArray? = null

    override fun setDefaultHeaders(headers: Map<String, String>) {
        httpFactory.setDefaultRequestProperties(headers)
    }

    override fun setAesKey(key: ByteArray) {
        require(key.size == 16) { "AES-128 key must be 16 bytes, got ${key.size}" }
        aesKey = key
    }

    /** Returns the existing cache or creates a fresh one if it was released. */
    private fun requireCache(): SimpleCache {
        // BUG FIX: was `simpleCache == null  cacheReleased` (missing ||)
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
        // The routing factory inspects each URI:
        //   /api/v1/proxy/dash/… → AesGcmDecryptingDataSource (decrypts GCM on the fly)
        //   everything else      → plain DefaultHttpDataSource (manifest, key endpoint, etc.)
        val routingFactory = RoutingDataSourceFactory(httpFactory, aesKey)

        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            CacheDataSource.Factory()
                .setCache(requireCache())
                .setUpstreamDataSourceFactory(routingFactory)
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

// ─── Internal routing factory ──────────────────────────────────────────────────

@OptIn(UnstableApi::class)
private class RoutingDataSourceFactory(
    private val httpFactory: DefaultHttpDataSource.Factory,
    private val aesKey: ByteArray?
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingDataSource(httpFactory, aesKey)
}

@OptIn(UnstableApi::class)
private class RoutingDataSource(
    private val httpFactory: DefaultHttpDataSource.Factory,
    private val aesKey: ByteArray?
) : DataSource {

    private var delegate: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri.toString()
        val key = aesKey
        delegate = if (key != null && isDashProxySegment(uri)) {
            // DASH media/init segment — decrypt with AES-128-GCM
            AesGcmDecryptingDataSource(
                aesKey      = key,
                httpFactory = httpFactory
            )
        } else {
            // Manifest, key-delivery, or anything else — plain HTTP
            httpFactory.createDataSource()
        }
        return delegate!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length)
            ?: throw IllegalStateException("RoutingDataSource not opened")

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        delegate?.close()
        delegate = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
        delegate?.addTransferListener(transferListener)
    }

    /** Only DASH proxy segments are AES-128-GCM encrypted. */
    private fun isDashProxySegment(uri: String) =
        uri.contains("/api/v1/proxy/dash/", ignoreCase = true)
}
