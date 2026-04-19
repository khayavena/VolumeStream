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
import java.io.IOException

@OptIn(UnstableApi::class)
class CachedPlaybackDataSourceFactoryImpl(
    private val context: Context,
    private val playerConfig: PlayerConfig = PlayerConfig(),
) : CachedPlaybackDataSourceFactory {

    private var simpleCache: SimpleCache? = null
    private var cacheReleased = true

    /** Live HTTP factory — headers updated via [setDefaultHeaders] without rebuilding ExoPlayer. */
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(playerConfig.httpConnectTimeoutMs)
        .setReadTimeoutMs(playerConfig.httpReadTimeoutMs)
        .setAllowCrossProtocolRedirects(true)

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
        // Pass a lambda so RoutingDataSource reads aesKey lazily at open() time.
        // This means setAesKey() can be called after buildCacheDataSourceFactory()
        // (e.g. when initPlayer reuses the same ExoPlayer) and the key is still picked up.
        val routingFactory = RoutingDataSourceFactory(
            httpFactory          = httpFactory,
            aesKeyProvider       = { aesKey },
            dashProxyPathFragment = playerConfig.dashProxyPathFragment
        )

        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            CacheDataSource.Factory()
                .setCache(requireCache())
                .setUpstreamDataSourceFactory(routingFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        )
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            // Stop retrying immediately on HTTP 401 instead of the default exponential
            // back-off. Without this, ExoPlayer hammers the server with the same revoked
            // token for tens of seconds, flooding the server log with TOKEN_INVALID warnings
            // and causing "Connection reset / Broken pipe" on segment responses.
            .setLoadErrorHandlingPolicy(Http401LoadErrorHandlingPolicy())
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
    private val aesKeyProvider: () -> ByteArray?,
    private val dashProxyPathFragment: String
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingDataSource(httpFactory, aesKeyProvider, dashProxyPathFragment)
}

@OptIn(UnstableApi::class)
private class RoutingDataSource(
    private val httpFactory: DefaultHttpDataSource.Factory,
    private val aesKeyProvider: () -> ByteArray?,
    private val dashProxyPathFragment: String
) : DataSource {

    private var delegate: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri.toString()
        val key = aesKeyProvider()
        delegate = when {
            isDashProxySegment(uri) && key != null -> {
                // DASH media/init segment — decrypt with AES-128-GCM
                AesGcmDecryptingDataSource(aesKey = key, httpFactory = httpFactory)
            }
            isDashProxySegment(uri) && key == null -> {
                // Key not yet delivered — throw so ExoPlayer retries after key arrival.
                // Silently falling through to plain HTTP would feed raw ciphertext to
                // the hardware H.264 decoder, causing "stream data corrupt" on every
                // frame while the codec does error-concealment on encrypted bytes.
                throw IOException(
                    "AES-128-GCM key not yet available for DASH segment — ExoPlayer will retry: $uri"
                )
            }
            else -> {
                // Manifest, key-delivery, HLS segments, or anything non-DASH-proxy — plain HTTP
                httpFactory.createDataSource()
            }
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

    /**
     * Only DASH proxy segments are AES-128-GCM encrypted.
     * Matched against [dashProxyPathFragment] (default "/api/v1/proxy/dash/")
     * so the routing is driven by [PlayerConfig.dashProxyPathFragment].
     *
     * This single check covers all encrypted DASH request types:
     *   - Video init segment:  /api/v1/proxy/dash/{id}/init?t=…
     *   - Audio init segment:  /api/v1/proxy/dash/{id}/init?t=…&stream=audio
     *   - Video media segment: /api/v1/proxy/dash/{id}/{N}?t=…
     *   - Audio media segment: /api/v1/proxy/dash/{id}/{N}?t=…&stream=audio
     *
     * HLS segments (/api/v1/proxy/{id}/{seg} and /api/v1/proxy/{id}/{quality}/{seg})
     * do NOT match this fragment and are served as plain HTTP; ExoPlayer decrypts
     * them natively via AES-128-CBC using the EXT-X-KEY from the HLS playlist.
     */
    private fun isDashProxySegment(uri: String) =
        uri.contains(dashProxyPathFragment, ignoreCase = true)
}
